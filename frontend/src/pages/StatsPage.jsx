import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer } from 'recharts'
import { getStats } from '../api/client'

export default function StatsPage() {
  const { shortCode } = useParams()
  const [stats, setStats] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    getStats(shortCode).then(setStats).catch((err) => setError(err.message))
  }, [shortCode])

  return (
    <div className="page">
      <Link to="/" className="back-link">&larr; Criar outro link</Link>

      <header className="app-header">
        <span className="app-header__logo">📊</span>
        <div>
          <h1 className="app-header__title">Estatísticas</h1>
          <p className="app-header__subtitle">/{shortCode}</p>
        </div>
      </header>

      {error && <p className="alert-error" role="alert">{error}</p>}

      {!error && !stats && <p className="empty-state">Carregando...</p>}

      {stats && (
        <>
          <div className="stats-grid">
            <div className="stat-tile">
              <p className="stat-tile__label">Total de cliques</p>
              <p className="stat-tile__value">{stats.totalClicks}</p>
            </div>
            <div className="stat-tile">
              <p className="stat-tile__label">Dias com cliques</p>
              <p className="stat-tile__value">{stats.dailySeries.length}</p>
            </div>
            <div className="stat-tile">
              <p className="stat-tile__label">Países</p>
              <p className="stat-tile__value">{Object.keys(stats.byCountry).length}</p>
            </div>
          </div>

          <div className="card">
            <h2 className="section-title">Cliques por dia</h2>
            {stats.dailySeries.length === 0 ? (
              <p className="empty-state">Ainda não há cliques registrados.</p>
            ) : (
              <ResponsiveContainer width="100%" height={260}>
                <LineChart data={stats.dailySeries}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="date" tick={{ fontSize: 12 }} />
                  <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                  <Tooltip />
                  <Line type="monotone" dataKey="count" stroke="#2563eb" strokeWidth={2} dot={{ r: 3 }} />
                </LineChart>
              </ResponsiveContainer>
            )}
          </div>

          <div className="card">
            <h2 className="section-title">Por país</h2>
            {Object.keys(stats.byCountry).length === 0 ? (
              <p className="empty-state">Sem dados ainda.</p>
            ) : (
              <ul className="breakdown-list">
                {Object.entries(stats.byCountry).map(([country, count]) => (
                  <li key={country}>
                    <span>{country}</span>
                    <span className="count">{count}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="card">
            <h2 className="section-title">Por dispositivo</h2>
            {Object.keys(stats.byDevice).length === 0 ? (
              <p className="empty-state">Sem dados ainda.</p>
            ) : (
              <ul className="breakdown-list">
                {Object.entries(stats.byDevice).map(([device, count]) => (
                  <li key={device}>
                    <span>{device}</span>
                    <span className="count">{count}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      )}
    </div>
  )
}
