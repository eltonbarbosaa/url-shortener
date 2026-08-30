import { Routes, Route } from 'react-router-dom'
import CreateLinkPage from './pages/CreateLinkPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<CreateLinkPage />} />
    </Routes>
  )
}
