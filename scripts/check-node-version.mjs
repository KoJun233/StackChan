const [major, minor] = process.versions.node.split('.').map(Number)
const supported = (major === 22 && minor >= 22) || (major === 24 && minor >= 15) || major >= 26

if (!supported) {
  throw new Error(
    `Fantastic-admin 6.3.0 requires Node 22.22.2+, 24.15.0+, or 26+; found ${process.versions.node}.`,
  )
}
