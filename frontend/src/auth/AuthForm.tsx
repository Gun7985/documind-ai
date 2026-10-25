import { useState } from 'react'
import type { FormEvent } from 'react'

type AuthFormProps = {
  submitLabel: string
  onSubmit: (email: string, password: string) => Promise<string | null>
}

export function AuthForm({ submitLabel, onSubmit }: AuthFormProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setMessage(null)
    try {
      setMessage(await onSubmit(email, password))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <label>
        이메일
        <input
          autoComplete="email"
          name="email"
          onChange={(event) => setEmail(event.target.value)}
          required
          type="email"
          value={email}
        />
      </label>
      <label>
        비밀번호
        <input
          autoComplete="current-password"
          minLength={8}
          name="password"
          onChange={(event) => setPassword(event.target.value)}
          required
          type="password"
          value={password}
        />
      </label>
      <button disabled={submitting} type="submit">
        {submitting ? '처리 중…' : submitLabel}
      </button>
      {message && <p role="alert">{message}</p>}
    </form>
  )
}
