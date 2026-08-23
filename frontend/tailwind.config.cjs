/** @type {import('tailwindcss').Config} */
module.exports = {
    content: ['./index.html', './src/**/*.{js,jsx}'],
    theme: {
        extend: {
            colors: {
                ink: {
                    DEFAULT: '#14161C',
                    soft: '#2A2D37',
                },
                paper: '#F5F6F8',
                surface: '#FFFFFF',
                line: '#E4E6EB',
                muted: '#6B7078',
                accent: {
                    DEFAULT: '#E7A335',
                    dark: '#B9791C',
                    ink: '#3A2A06',
                    soft: '#FCEFD7',
                },
                status: {
                    applied: '#6B7280',
                    appliedSoft: '#EEF0F2',
                    screening: '#B9790C',
                    screeningSoft: '#FBF0DA',
                    interview: '#2354D9',
                    interviewSoft: '#E8EEFC',
                    offer: '#1F9254',
                    offerSoft: '#E4F5EC',
                    rejected: '#C4432B',
                    rejectedSoft: '#FBEAE6',
                    withdrawn: '#8B8F98',
                    withdrawnSoft: '#EFEFF1',
                },
            },
            fontFamily: {
                display: ['"Fraunces"', 'ui-serif', 'Georgia', 'serif'],
                sans: ['"Inter"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
                mono: ['"IBM Plex Mono"', 'ui-monospace', 'SFMono-Regular', 'monospace'],
            },
            boxShadow: {
                card: '0 1px 2px rgba(20, 22, 28, 0.04), 0 1px 12px rgba(20, 22, 28, 0.04)',
                pop: '0 8px 24px rgba(20, 22, 28, 0.12)',
            },
            borderRadius: {
                xl2: '0.875rem',
            },
        },
    },
    plugins: [],
}
