import { NavLink, Outlet, Route, Routes, useNavigate } from 'react-router'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { useAuth } from './auth/AuthContext'
import { ForgotPasswordPage, LoginPage, ResetPasswordPage, SignupPage } from './pages/AuthPages'

const privateRoutes = [
  { path: '/dashboard', label: '대시보드', heading: '대시보드' },
  { path: '/documents', label: '문서', heading: '문서 목록' },
  { path: '/documents/upload', label: '업로드', heading: 'PDF 업로드' },
  { path: '/settings', label: '설정', heading: '설정' },
] as const

function Layout() {
  const { signOut, user } = useAuth()
  const navigate = useNavigate()

  async function handleSignOut() {
    const result = await signOut()
    if (!result.error) navigate('/login')
  }

  return (
    <div className="app-shell">
      <header>
        <NavLink className="brand" to="/">DocuMind AI</NavLink>
        <nav aria-label="주요 메뉴">
          {privateRoutes.map((route) => (
            <NavLink key={route.path} to={route.path}>{route.label}</NavLink>
          ))}
          {!user && <NavLink to="/login">로그인</NavLink>}
          {!user && <NavLink to="/signup">회원가입</NavLink>}
          {user && <button className="link-button" onClick={handleSignOut} type="button">로그아웃</button>}
        </nav>
      </header>
      <main><Outlet /></main>
      <footer>Phase 3A authentication and database foundation</footer>
    </div>
  )
}

function PlaceholderPage({ heading }: { heading: string }) {
  return (
    <section>
      <p className="eyebrow">기본 화면</p>
      <h1>{heading}</h1>
      <p>이 페이지의 실제 업무 기능은 Phase 3B 이후에 구현합니다.</p>
    </section>
  )
}

function HomePage() {
  const { configured } = useAuth()
  return (
    <section>
      <p className="eyebrow">Secure document workspace</p>
      <h1>DocuMind AI</h1>
      <p>텍스트 기반 PDF 분석을 위한 인증 기반이 준비되었습니다.</p>
      {!configured && <p role="status">로컬 인증을 사용하려면 공개 Supabase 환경변수를 설정하세요.</p>}
    </section>
  )
}

function NotFoundPage() {
  return (
    <section>
      <p className="eyebrow">404</p>
      <h1>페이지를 찾을 수 없습니다</h1>
      <NavLink to="/">홈으로 돌아가기</NavLink>
    </section>
  )
}

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route element={<ProtectedRoute />}>
          {privateRoutes.map((route) => (
            <Route key={route.path} path={route.path} element={<PlaceholderPage heading={route.heading} />} />
          ))}
          <Route path="/documents/:id" element={<PlaceholderPage heading="문서 상세" />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
