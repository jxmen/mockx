import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { MemberIdProvider } from './auth/MemberIdProvider'
import { Header } from './components/Header'
import { SignupPage } from './features/signup/SignupPage'

function App() {
  return (
    <MemberIdProvider>
      <BrowserRouter>
        <Header />
        <Routes>
          <Route path="/" element={<Navigate to="/signup" replace />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="*" element={<Navigate to="/signup" replace />} />
        </Routes>
      </BrowserRouter>
    </MemberIdProvider>
  )
}

export default App
