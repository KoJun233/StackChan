Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: () => ({
    addEventListener: () => {},
    matches: false,
    removeEventListener: () => {},
  }),
})
