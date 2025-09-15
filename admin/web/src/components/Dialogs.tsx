import { useEffect, useState } from 'react'
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField, Typography } from '@mui/material'

export function ConfirmDialog({ open, title = 'Confirm', message, confirmText = 'Confirm', cancelText = 'Cancel', onClose }:
  { open: boolean; title?: string; message: string; confirmText?: string; cancelText?: string; onClose: (confirmed: boolean) => void }) {
  return (
    <Dialog open={open} onClose={() => onClose(false)} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Typography>{message}</Typography>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => onClose(false)}>{cancelText}</Button>
        <Button onClick={() => onClose(true)} variant="contained" color="error">{confirmText}</Button>
      </DialogActions>
    </Dialog>
  )
}

export function TwoFieldDialog({ open, title, aLabel, bLabel, aRequired = true, bRequired = true, initialA = '', initialB = '', onSubmit, onClose }:
  { open: boolean; title: string; aLabel: string; bLabel: string; aRequired?: boolean; bRequired?: boolean; initialA?: string; initialB?: string; onSubmit: (a: string, b: string) => Promise<void> | void; onClose: () => void }) {
  const [a, setA] = useState(initialA)
  const [b, setB] = useState(initialB)
  const [errors, setErrors] = useState<{ a?: string; b?: string }>({})
  const [busy, setBusy] = useState(false)

  useEffect(() => { setA(initialA); setB(initialB); setErrors({}); setBusy(false) }, [open, initialA, initialB])

  const validate = () => {
    const e: { a?: string; b?: string } = {}
    if (aRequired && !a.trim()) e.a = 'Required'
    if (bRequired && !b.trim()) e.b = 'Required'
    setErrors(e)
    return Object.keys(e).length === 0
  }
  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!validate()) return
    setBusy(true)
    try { await onSubmit(a.trim(), b.trim()); onClose() } finally { setBusy(false) }
  }
  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Stack component="form" onSubmit={submit} spacing={2} sx={{ pt: 1 }}>
          <TextField label={aLabel} value={a} onChange={(e) => setA(e.target.value)} error={!!errors.a} helperText={errors.a} required={aRequired} fullWidth autoFocus />
          <TextField label={bLabel} value={b} onChange={(e) => setB(e.target.value)} error={!!errors.b} helperText={errors.b} required={bRequired} fullWidth />
          <Stack direction="row" justifyContent="flex-end" spacing={1}>
            <Button onClick={onClose} disabled={busy}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={busy}>Save</Button>
          </Stack>
        </Stack>
      </DialogContent>
    </Dialog>
  )
}

export function SingleFieldDialog({ open, title, label, required = true, initial = '', onSubmit, onClose }:
  { open: boolean; title: string; label: string; required?: boolean; initial?: string; onSubmit: (value: string) => Promise<void> | void; onClose: () => void }) {
  const [v, setV] = useState(initial)
  const [err, setErr] = useState<string | undefined>()
  const [busy, setBusy] = useState(false)
  useEffect(() => { setV(initial); setErr(undefined); setBusy(false) }, [open, initial])
  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    const val = v.trim()
    if (required && !val) { setErr('Required'); return }
    setBusy(true)
    try { await onSubmit(val); onClose() } finally { setBusy(false) }
  }
  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Stack component="form" onSubmit={submit} spacing={2} sx={{ pt: 1 }}>
          <TextField label={label} value={v} onChange={(e) => setV(e.target.value)} error={!!err} helperText={err} required={required} fullWidth autoFocus />
          <Stack direction="row" justifyContent="flex-end" spacing={1}>
            <Button onClick={onClose} disabled={busy}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={busy}>Save</Button>
          </Stack>
        </Stack>
      </DialogContent>
    </Dialog>
  )
}
