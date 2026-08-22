export class User {
    id!: number;
    firstName!: string;
    lastName!: string;
    username!: string;
    password!: string;
    securityLevel!: number;
    email!: string;
    role!: string;
    isActive!: boolean;
    token?: string;
}
