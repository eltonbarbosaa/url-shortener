import { Routes, Route } from 'react-router-dom'
import CreateLinkPage from './pages/CreateLinkPage'
import StatsPage from './pages/StatsPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<CreateLinkPage />} />
      <Route path="/stats/:shortCode" element={<StatsPage />} />
    </Routes>
  )
}
