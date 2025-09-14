export type User = {
  id: string;
  username: string; // lowercased email for Google users
  createdAt: string;
  lastLogin?: string;
};

export type GoogleProfile = {
  sub?: string;
  email?: string;
  name?: string;
  picture?: string;
  iss?: string;
  exp?: number;
};

export type AuthResult = {
  user: User;
  profile: GoogleProfile;
  created: boolean; // true when a new user was created
};
