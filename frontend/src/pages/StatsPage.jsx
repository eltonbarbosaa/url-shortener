import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer } from 'recharts'
import { getStats } from '../api/client'

export default function StatsPage() {
  const { shortCode } = useParams()
  const [stats, setStats] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    getStats(shortCode).then(setStats).catch((err) => setError(err.message))
  }, [shortCode])

  if (error) {
    return <p role="alert">{error}</p>
  }

  if (!stats) {
    return <p>Carregando...</p>
  }

  return (
    <div>
      <h1>Estatísticas de {shortCode}</h1>
      <p>Total de cliques: {stats.totalClicks}</p>

      <h2>Cliques por dia</h2>
      <ResponsiveContainer width="100%" height={300}>
        <LineChart data={stats.dailySeries}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="date" />
          <YAxis allowDecimals={false} />
          <Tooltip />
          <Line type="monotone" dataKey="count" stroke="#2563eb" />
        </LineChart>
      </ResponsiveContainer>

      <h2>Por país</h2>
      <ul>
        {Object.entries(stats.byCountry).map(([country, count]) => (
          <li key={country}>{country}: {count}</li>
        ))}
      </ul>

      <h2>Por dispositivo</h2>
      <ul>
        {Object.entries(stats.byDevice).map(([device, count]) => (
          <li key={device}>{device}: {count}</li>
        ))}
      </ul>
    </div>
  )
}
