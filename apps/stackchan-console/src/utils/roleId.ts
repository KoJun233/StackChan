const CANONICAL_UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/**
 * Companion roles include the reserved default ID
 * 00000000-0000-0000-0000-000000000001. PostgreSQL accepts it as UUID,
 * but strict RFC UUID validators reject it because it has no version bits.
 */
export function isCompanionRoleId(value: string): boolean {
  return CANONICAL_UUID_PATTERN.test(value.trim())
}
