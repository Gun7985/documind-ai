import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router'
import { AuthForm } from '../auth/AuthForm'
import { useAuth } from '../auth/AuthContext'

export function LoginPage() {
  const { signIn, user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const destination = (location.state as { from?: string } | null)?.from ?? '/dashboard'

  if (user) return <Navigate to={destination} replace />

  return (
    <section>
      <p className="eyebrow">Supabase Auth</p>
      <h1>로그인</h1>
      {searchParams.get('confirmed') === 'true' && <p role="status">이메일 인증이 완료되었습니다.</p>}
      <AuthForm
        submitLabel="로그인"
        onSubmit={async (email, password) => {
          const result = await signIn(email, password)
          if (!result.error) navigate(destination, { replace: true })
          return result.error
        }}
      />
      <p><Link to="/forgot-password">비밀번호를 잊으셨나요?</Link></p>
      <p><Link to="/signup">계정 만들기</Link></p>
    </section>
  )
}

export function SignupPage() {
  const { signUp, user } = useAuth()
  if (user) return <Navigate to="/dashboard" replace />

  return (
    <section>
      <p className="eyebrow">Supabase Auth</p>
      <h1>회원가입</h1>
      <AuthForm
        submitLabel="회원가입"
        onSubmit={async (email, password) => {
          const result = await signUp(email, password)
          if (result.error) return result.error
          return result.confirmationRequired
            ? '가입 확인 이메일을 보냈습니다. 이메일의 링크를 열어 인증을 완료해 주세요.'
            : '회원가입이 완료되었습니다.'
        }}
      />
    </section>
  )
}

export function ForgotPasswordPage() {
  const { requestPasswordReset } = useAuth()
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState<string | null>(null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const result = await requestPasswordReset(email)
    setMessage(result.error ?? '계정이 존재하면 비밀번호 재설정 이메일을 보냅니다.')
  }

  return (
    <section>
      <h1>비밀번호 재설정 요청</h1>
      <form onSubmit={submit}>
        <label>
          이메일
          <input required type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
        </label>
        <button type="submit">재설정 이메일 보내기</button>
      </form>
      {message && <p role="alert">{message}</p>}
    </section>
  )
}

export function ResetPasswordPage() {
  const { updatePassword, user } = useAuth()
  const [password, setPassword] = useState('')
  const [message, setMessage] = useState<string | null>(null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const result = await updatePassword(password)
    setMessage(result.error ?? '비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.')
  }

  if (!user) {
    return (
      <section>
        <h1>비밀번호 재설정</h1>
        <p role="alert">재설정 이메일의 링크를 통해 이 페이지를 열어 주세요.</p>
      </section>
    )
  }

  return (
    <section>
      <h1>새 비밀번호 설정</h1>
      <form onSubmit={submit}>
        <label>
          새 비밀번호
          <input
            minLength={8}
            required
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </label>
        <button type="submit">비밀번호 변경</button>
      </form>
      {message && <p role="alert">{message}</p>}
    </section>
  )
}
