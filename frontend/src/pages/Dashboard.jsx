import React, { useContext, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Plus, Briefcase, MessagesSquare, PartyPopper, XCircle, ArrowRight } from 'lucide-react'
import api from '../api/axios'
import Layout from '../components/Layout'
import StatCard from '../components/StatCard'
import PipelineBar from '../components/PipelineBar'
import ApplicationCard from '../components/ApplicationCard'
import ApplicationDrawer from '../components/ApplicationDrawer'
import { AuthContext } from '../context/AuthContext'

const emptyForm = { companyName: '', roleTitle: '', jobDescription: '', status: 'APPLIED', appliedDate: '' }

export default function Dashboard() {
  const { user } = useContext(AuthContext)
  const [applications, setApplications] = useState([])
  const [loading, setLoading] = useState(true)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [form, setForm] = useState(emptyForm)

  useEffect(() => { fetchApps() }, [])

  async function fetchApps() {
    try {
      setLoading(true)
      const res = await api.get('/applications')
      setApplications(res.data)
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }

  async function submitApp(e) {
    e.preventDefault()
    try {
      await api.post('/applications', form)
      setForm(emptyForm)
      setDrawerOpen(false)
      fetchApps()
    } catch (e) {
      console.error(e)
    }
  }

  const total = applications.length
  const interviews = applications.filter((a) => a.status === 'INTERVIEW').length
  const offers = applications.filter((a) => a.status === 'OFFER').length
  const rejections = applications.filter((a) => a.status === 'REJECTED').length
  const firstName = (user?.profile?.name || '').split(' ')[0]

  return (
    <Layout
      title={firstName ? `Welcome back, ${firstName}` : 'Dashboard'}
      subtitle="Here's where your search stands today"
      actions={
        <button
          onClick={() => { setForm(emptyForm); setDrawerOpen(true) }}
          className="inline-flex items-center gap-1.5 bg-ink text-white text-sm font-medium px-3.5 py-2 rounded-lg hover:bg-ink-soft"
        >
          <Plus size={15} /> Add application
        </button>
      }
    >
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <StatCard label="Total applications" value={total} icon={Briefcase} accent />
        <StatCard label="Interviews" value={interviews} icon={MessagesSquare} />
        <StatCard label="Offers" value={offers} icon={PartyPopper} />
        <StatCard label="Rejections" value={rejections} icon={XCircle} />
      </div>

      <div className="mb-6">
        <PipelineBar applications={applications} />
      </div>

      <div className="flex items-center justify-between mb-3">
        <h2 className="font-display text-lg text-ink">Recent applications</h2>
        <Link to="/applications" className="text-sm font-medium text-ink inline-flex items-center gap-1 hover:text-accent-dark">
          View all <ArrowRight size={14} />
        </Link>
      </div>

      {loading ? (
        <div className="text-sm text-muted">Loading your applications…</div>
      ) : applications.length === 0 ? (
        <div className="bg-surface border border-dashed border-line rounded-xl2 p-10 text-center">
          <p className="font-display text-ink text-lg mb-1">No applications yet</p>
          <p className="text-sm text-muted mb-4">Add the first role you've applied to — it takes a few seconds.</p>
          <button
            onClick={() => setDrawerOpen(true)}
            className="inline-flex items-center gap-1.5 bg-ink text-white text-sm font-medium px-4 py-2 rounded-lg"
          >
            <Plus size={15} /> Add application
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          {applications.slice(0, 5).map((a) => (
            <ApplicationCard key={a.id} app={a} />
          ))}
        </div>
      )}

      <ApplicationDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        form={form}
        setForm={setForm}
        onSubmit={submitApp}
        isEditing={false}
      />
    </Layout>
  )
}
