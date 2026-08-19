/** @type {import('tailwindcss').Config} */
export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        brand: {
          50: "#f2f0ff",
          100: "#e7e3ff",
          200: "#d1c9ff",
          300: "#b3a2ff",
          400: "#9370ff",
          500: "#7c4dff",
          600: "#6c2bff",
          700: "#5a1de0",
          800: "#4715ad",
          900: "#331089",
        },
        accent: {
          cyan: "#22e8d8",
          pink: "#ff4fd8",
          amber: "#ffb020",
          lime: "#a3ff3c",
        },
        surface: {
          light: "#f6f5ff",
          dark: "#0b0a17",
          darkcard: "#141327",
          darkraise: "#1c1a35",
        },
      },
      fontFamily: {
        display: ["'Space Grotesk'", "system-ui", "sans-serif"],
        sans: ["'Inter'", "system-ui", "sans-serif"],
      },
      keyframes: {
        "typing-dot": {
          "0%, 60%, 100%": { transform: "translateY(0)", opacity: "0.4" },
          "30%": { transform: "translateY(-4px)", opacity: "1" },
        },
        "fade-in": {
          from: { opacity: "0", transform: "translateY(4px)" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
        "pop-in": {
          "0%": { opacity: "0", transform: "scale(0.85) translateY(6px)" },
          "60%": { opacity: "1", transform: "scale(1.03) translateY(0)" },
          "100%": { opacity: "1", transform: "scale(1) translateY(0)" },
        },
        "gradient-shift": {
          "0%, 100%": { backgroundPosition: "0% 50%" },
          "50%": { backgroundPosition: "100% 50%" },
        },
        "float-slow": {
          "0%, 100%": { transform: "translate(0, 0) scale(1)" },
          "50%": { transform: "translate(-3%, 4%) scale(1.08)" },
        },
        "float-slower": {
          "0%, 100%": { transform: "translate(0, 0) scale(1)" },
          "50%": { transform: "translate(4%, -3%) scale(1.05)" },
        },
        "glow-pulse": {
          "0%, 100%": { boxShadow: "0 0 0px 0px rgba(124,77,255,0.0)" },
          "50%": { boxShadow: "0 0 24px 4px rgba(124,77,255,0.45)" },
        },
        "shimmer": {
          "0%": { backgroundPosition: "-200% 0" },
          "100%": { backgroundPosition: "200% 0" },
        },
        "streak-flicker": {
          "0%, 100%": { transform: "rotate(-6deg) scale(1)" },
          "50%": { transform: "rotate(6deg) scale(1.15)" },
        },
        "ring-expand": {
          "0%": { transform: "scale(0.6)", opacity: "0.8" },
          "100%": { transform: "scale(2.2)", opacity: "0" },
        },
        "spark": {
          "0%": { transform: "scale(0) rotate(0deg)", opacity: "0" },
          "40%": { opacity: "1" },
          "100%": { transform: "scale(1.4) rotate(30deg)", opacity: "0" },
        },
      },
      animation: {
        "typing-dot": "typing-dot 1.2s infinite",
        "fade-in": "fade-in 0.15s ease-out",
        "pop-in": "pop-in 0.35s cubic-bezier(0.34,1.56,0.64,1)",
        "gradient-shift": "gradient-shift 8s ease infinite",
        "float-slow": "float-slow 12s ease-in-out infinite",
        "float-slower": "float-slower 16s ease-in-out infinite",
        "glow-pulse": "glow-pulse 2.4s ease-in-out infinite",
        "shimmer": "shimmer 2.5s linear infinite",
        "streak-flicker": "streak-flicker 1.8s ease-in-out infinite",
        "ring-expand": "ring-expand 1.6s ease-out infinite",
        "spark": "spark 0.6s ease-out forwards",
      },
      backgroundSize: {
        "300%": "300% 300%",
      },
    },
  },
  plugins: [],
};
