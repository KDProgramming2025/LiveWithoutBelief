import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useState } from 'react';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField, Typography } from '@mui/material';
export function ConfirmDialog({ open, title = 'Confirm', message, confirmText = 'Confirm', cancelText = 'Cancel', onClose }) {
    return (_jsxs(Dialog, { open: open, onClose: () => onClose(false), maxWidth: "xs", fullWidth: true, children: [_jsx(DialogTitle, { children: title }), _jsx(DialogContent, { children: _jsx(Typography, { children: message }) }), _jsxs(DialogActions, { children: [_jsx(Button, { onClick: () => onClose(false), children: cancelText }), _jsx(Button, { onClick: () => onClose(true), variant: "contained", color: "error", children: confirmText })] })] }));
}
export function TwoFieldDialog({ open, title, aLabel, bLabel, aRequired = true, bRequired = true, initialA = '', initialB = '', onSubmit, onClose }) {
    const [a, setA] = useState(initialA);
    const [b, setB] = useState(initialB);
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    useEffect(() => { setA(initialA); setB(initialB); setErrors({}); setBusy(false); }, [open, initialA, initialB]);
    const validate = () => {
        const e = {};
        if (aRequired && !a.trim())
            e.a = 'Required';
        if (bRequired && !b.trim())
            e.b = 'Required';
        setErrors(e);
        return Object.keys(e).length === 0;
    };
    const submit = async (e) => {
        e.preventDefault();
        if (!validate())
            return;
        setBusy(true);
        try {
            await onSubmit(a.trim(), b.trim());
            onClose();
        }
        finally {
            setBusy(false);
        }
    };
    return (_jsxs(Dialog, { open: open, onClose: onClose, maxWidth: "sm", fullWidth: true, children: [_jsx(DialogTitle, { children: title }), _jsx(DialogContent, { children: _jsxs(Stack, { component: "form", onSubmit: submit, spacing: 2, sx: { pt: 1 }, children: [_jsx(TextField, { label: aLabel, value: a, onChange: (e) => setA(e.target.value), error: !!errors.a, helperText: errors.a, required: aRequired, fullWidth: true, autoFocus: true }), _jsx(TextField, { label: bLabel, value: b, onChange: (e) => setB(e.target.value), error: !!errors.b, helperText: errors.b, required: bRequired, fullWidth: true }), _jsxs(Stack, { direction: "row", justifyContent: "flex-end", spacing: 1, children: [_jsx(Button, { onClick: onClose, disabled: busy, children: "Cancel" }), _jsx(Button, { type: "submit", variant: "contained", disabled: busy, children: "Save" })] })] }) })] }));
}
export function SingleFieldDialog({ open, title, label, required = true, initial = '', onSubmit, onClose }) {
    const [v, setV] = useState(initial);
    const [err, setErr] = useState();
    const [busy, setBusy] = useState(false);
    useEffect(() => { setV(initial); setErr(undefined); setBusy(false); }, [open, initial]);
    const submit = async (e) => {
        e.preventDefault();
        const val = v.trim();
        if (required && !val) {
            setErr('Required');
            return;
        }
        setBusy(true);
        try {
            await onSubmit(val);
            onClose();
        }
        finally {
            setBusy(false);
        }
    };
    return (_jsxs(Dialog, { open: open, onClose: onClose, maxWidth: "xs", fullWidth: true, children: [_jsx(DialogTitle, { children: title }), _jsx(DialogContent, { children: _jsxs(Stack, { component: "form", onSubmit: submit, spacing: 2, sx: { pt: 1 }, children: [_jsx(TextField, { label: label, value: v, onChange: (e) => setV(e.target.value), error: !!err, helperText: err, required: required, fullWidth: true, autoFocus: true }), _jsxs(Stack, { direction: "row", justifyContent: "flex-end", spacing: 1, children: [_jsx(Button, { onClick: onClose, disabled: busy, children: "Cancel" }), _jsx(Button, { type: "submit", variant: "contained", disabled: busy, children: "Save" })] })] }) })] }));
}
