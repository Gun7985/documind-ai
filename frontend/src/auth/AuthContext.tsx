import type { AuthError, Session, User } from '@supabase/supabase-js'
import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { isSupabaseConfigured, supabase } from '../lib/supabase'

type AuthResult = {
  error: string | null
  confirmationRequired?: boolean
}

export type AuthContextValue = {
  configured: boolean
  loading: boolean
  session: Session | null
  user: User | null
  signUp: (email: string, password: string) => Promise<AuthResult>
  signIn: (email: string, password: string) => Promise<AuthResult>
  signOut: () => Promise<AuthResult>
  requestPasswordReset: (email: string) => Promise<AuthResult>
  updatePassword: (password: string) => Promise<AuthResult>
}

const configurationError = 'Supabase 공개 환경변수가 설정되지 않았습니다.'

function safeAuthMessage(error: AuthError): string {
  switch (error.code) {
    case 'invalid_credentials':
      return '이메일 또는 비밀번호를 확인해 주세요.'
    case 'email_not_confirmed':
      return '이메일 인증을 완료한 뒤 다시 로그인해 주세요.'
    case 'user_already_exists':
      return '이미 가입된 이메일입니다.'
    case 'weak_password':
      return '더 안전한 비밀번호를 사용해 주세요.'
    default:
      return '인증 요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  }
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!supabase) {
      setLoading(false)
      return
    }

    let active = true
    void supabase.auth.getSession().then(({ data }) => {
      if (active) {
        setSession(data.session)
        setLoading(false)
      }
    })

    const { data } = supabase.auth.onAuthStateChange((_event, nextSession) => {
      setSession(nextSession)
      setLoading(false)
    })

    return () => {
      active = false
      data.subscription.unsubscribe()
    }
  }, [])

  const value = useMemo<AuthContextValue>(() => ({
    configured: isSupabaseConfigured,
    loading,
    session,
    user: session?.user ?? null,
    async signUp(email, password) {
      if (!supabase) return { error: configurationError }
      const { data, error } = await supabase.auth.signUp({
        email,
        password,
        options: { emailRedirectTo: `${window.location.origin}/login?confirmed=true` },
      })
      return error
        ? { error: safeAuthMessage(error) }
        : { error: null, confirmationRequired: data.session === null }
    },
    async signIn(email, password) {
      if (!supabase) return { error: configurationError }
      const { error } = await supabase.auth.signInWithPassword({ email, password })
      return { error: error ? safeAuthMessage(error) : null }
    },
    async signOut() {
      if (!supabase) return { error: configurationError }
      const { error } = await supabase.auth.signOut()
      return { error: error ? safeAuthMessage(error) : null }
    },
    async requestPasswordReset(email) {
      if (!supabase) return { error: configurationError }
      const { error } = await supabase.auth.resetPasswordForEmail(email, {
        redirectTo: `${window.location.origin}/reset-password`,
      })
      return { error: error ? safeAuthMessage(error) : null }
    },
    async updatePassword(password) {
      if (!supabase) return { error: configurationError }
      const { error } = await supabase.auth.updateUser({ password })
      return { error: error ? safeAuthMessage(error) : null }
    },
  }), [loading, session])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}
