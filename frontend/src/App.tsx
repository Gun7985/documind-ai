import { NavLink, Outlet, Route, Routes } from 'react-router'

const routes = [
  { path: '/', label: '홈', heading: 'DocuMind AI' },
  { path: '/login', label: '로그인', heading: '로그인' },
  { path: '/signup', label: '회원가입', heading: '회원가입' },
  { path: '/dashboard', label: '대시보드', heading: '대시보드' },
  { path: '/documents', label: '문서', heading: '문서 목록' },
  { path: '/documents/upload', label: '업로드', heading: 'PDF 업로드' },
  { path: '/settings', label: '설정', heading: '설정' },
] as const

function Layout() {
  return (
    <div className="app-shell">
      <header>
        <NavLink className="brand" to="/">
          DocuMind AI
        </NavLink>
        <nav aria-label="주요 메뉴">
          {routes.slice(1).map((route) => (
            <NavLink key={route.path} to={route.path}>
              {route.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
      <footer>Phase 2 application bootstrap</footer>
    </div>
  )
}

function PlaceholderPage({ heading }: { heading: string }) {
  return (
    <section>
      <p className="eyebrow">기본 화면</p>
      <h1>{heading}</h1>
      <p>이 페이지의 실제 업무 기능은 이후 단계에서 구현합니다.</p>
    </section>
  )
}

function DocumentDetailPage() {
  return <PlaceholderPage heading="문서 상세" />
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
        {routes.map((route) => (
          <Route
            key={route.path}
            path={route.path}
            element={<PlaceholderPage heading={route.heading} />}
          />
        ))}
        <Route path="/documents/:id" element={<DocumentDetailPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
