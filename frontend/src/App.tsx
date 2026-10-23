import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { MemberIdProvider } from './auth/MemberIdProvider'
import { Header } from './components/Header'
import { DepositPage } from './features/deposit/DepositPage'
import { HomePage } from './features/home/HomePage'
import { MyInfoPage } from './features/me/MyInfoPage'
import { SignupPage } from './features/signup/SignupPage'
import { StockDetailPage } from './features/stocks/StockDetailPage'
import { StocksPage } from './features/stocks/StocksPage'

function App() {
  return (
    <MemberIdProvider>
      <BrowserRouter>
        <Header />
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/me" element={<MyInfoPage />} />
          <Route path="/deposit" element={<DepositPage />} />
          <Route path="/stocks" element={<StocksPage />} />
          <Route path="/stocks/:symbol" element={<StockDetailPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </MemberIdProvider>
  )
}

export default App
