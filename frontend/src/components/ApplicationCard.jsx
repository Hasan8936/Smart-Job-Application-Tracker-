import React from 'react'

export default function ApplicationCard({ app, onEdit }){
  return (
    <div className="p-4 bg-white rounded shadow flex justify-between items-start">
      <div>
        <div className="font-semibold">{app.companyName} — {app.roleTitle}</div>
        <div className="text-sm text-gray-600 mt-1">Status: <span className="font-medium">{app.status}</span></div>
        <div className="text-sm text-gray-500 mt-2">{app.jobDescription?.slice(0, 180)}</div>
      </div>
      <div className="flex flex-col gap-2">
        <button onClick={() => onEdit(app)} className="text-sm bg-yellow-400 px-3 py-1 rounded">Edit</button>
      </div>
    </div>
  )
}
