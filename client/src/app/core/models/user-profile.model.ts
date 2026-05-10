export type Tier = 'FREE' | 'PREMIUM' | 'ARCHIVE';
export type UserRole = 'OWNER' | 'ADMIN' | 'VIEWER';

export interface UserProfile {
  id: string;
  email: string;
  displayName: string;
  tier: Tier;
  role: UserRole;
}
