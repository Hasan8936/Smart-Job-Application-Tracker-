/** @type {import('tailwindcss').Config} */
module.exports = {
    content: ['./index.html', './src/**/*.{js,jsx}'],
    theme: {
        extend: {
            colors: {
                // Visitors-style blueprint palette
                ink: {
                    DEFAULT: '#181925', // carbon
                    soft: '#666666', // graphite
                },
                paper: '#fafafa', // linen — page canvas
                surface: '#ffffff', // paper white — card/header fill
                mist: '#f5f5f5',
                line: '#e8e8e8', // fog — hairline borders
                muted: '#999999', // ash
                carbon: '#181925',
                graphite: '#666666',
                ash: '#999999',
                fog: '#e8e8e8',
                linen: '#fafafa',
                accent: {
                    DEFAULT: '#6D5DF6', // violet
                    dark: '#5A4AE3',
                    ink: '#ffffff',
                    soft: '#EEECFE',
                },
                lavender: '#918df6',
                iris: '#9B5DE5',
                pink: '#EC5FA0',
                mint: '#33c758',
                mintWash: '#def6e4',
                amber: '#ffa600',
                sky: '#2c78fc',
                magenta: '#EC5FA0',
                ember: '#ff3e00',
                status: {
                    applied: '#666666',
                    appliedSoft: '#f5f5f5',
                    screening: '#c47f00',
                    screeningSoft: '#fff4df',
                    interview: '#2c78fc',
                    interviewSoft: '#e7f0fe',
                    offer: '#1fa348',
                    offerSoft: '#def6e4',
                    rejected: '#e0380d',
                    rejectedSoft: '#ffe9e0',
                    withdrawn: '#999999',
                    withdrawnSoft: '#f5f5f5',
                },
            },
            fontFamily: {
                display: ['"DM Sans"', '"Inter"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
                sans: ['"Inter"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
                mono: ['"IBM Plex Mono"', 'ui-monospace', 'SFMono-Regular', 'monospace'],
            },
            letterSpacing: {
                tightest: '-0.05em',
            },
            boxShadow: {
                card: '0 1px 3px rgba(0, 0, 0, 0.06), 0 8px 16px rgba(0, 0, 0, 0.06), 0 0 0 1px rgba(0, 0, 0, 0.02)',
                pop: '0 1px 1px rgba(0, 0, 0, 0.08), 0 0 0 0.5px rgba(0, 0, 0, 0.06)',
                subtle: '0 1px 1px 1px rgba(0, 0, 0, 0.08), 0 0 0 0.5px rgba(0, 0, 0, 0.06)',
                soft: '0 4px 24px rgba(24, 25, 37, 0.06)',
                glow: '0 12px 24px -8px rgba(109, 93, 246, 0.45)',
                floaty: '0 20px 45px -12px rgba(109, 93, 246, 0.35)',
            },
            borderRadius: {
                xl2: '1rem',
                xl3: '1.5rem',
            },
        },
    },
    plugins: [],
}
