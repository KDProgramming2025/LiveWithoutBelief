"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const vitest_1 = require("vitest");
const AuthService_1 = require("../../src/services/AuthService");
const UserRepository_1 = require("../../src/repositories/UserRepository");
(0, vitest_1.describe)('AuthService', () => {
    (0, vitest_1.it)('registers by email (upsert semantics)', async () => {
        const svc = new AuthService_1.AuthService(new UserRepository_1.InMemoryUserRepository());
        const a = await svc.registerByEmail('a@example.com');
        const b = await svc.registerByEmail('a@example.com');
        (0, vitest_1.expect)(a.created).toBe(true);
        (0, vitest_1.expect)(b.created).toBe(false);
        (0, vitest_1.expect)(a.user.id).toBeTypeOf('string');
        (0, vitest_1.expect)(b.user.id).toEqual(a.user.id);
    });
    (0, vitest_1.it)('password register/login flow', async () => {
        const svc = new AuthService_1.AuthService(new UserRepository_1.InMemoryUserRepository());
        const user = await svc.passwordRegister('alice', 'secret');
        (0, vitest_1.expect)(user).toBeTruthy();
        const dup = await svc.passwordRegister('alice', 'secret');
        (0, vitest_1.expect)(dup).toBeNull();
        const ok = await svc.passwordLogin('alice', 'secret');
        (0, vitest_1.expect)(ok?.id).toEqual(user?.id);
        const bad = await svc.passwordLogin('alice', 'nope');
        (0, vitest_1.expect)(bad).toBeNull();
    });
});
// intentionally empty (TS test exists)
