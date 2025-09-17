declare module 'altcha';
declare module 'altcha-lib';
declare module 'google-auth-library';
declare module 'argon2';
declare module 'pg';

declare global {
	namespace NodeJS {
		interface ProcessEnv {
			PORT?: string
			ALTCHA_SECRET?: string
		}
	}
}

declare namespace Express {
	interface Request {
		file?: {
			path: string
			filename?: string
			mimetype?: string
			size?: number
		}
	}
}
