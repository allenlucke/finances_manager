import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { first } from 'rxjs/operators';

import { environment } from 'src/environments/environment';
import { User } from '../_models/user';
import { UserLocalStorage } from '../_models/user-local-storage';

@Injectable({ providedIn: 'root' })
export class AuthenticationService {
    private currentUserSubject: BehaviorSubject<User>;
    public currentUser: Observable<User>;

    currentUserId! : number;
    userLocalData! : UserLocalStorage[];

    constructor(private http: HttpClient) {
        this.currentUserSubject = new BehaviorSubject<User>(JSON.parse(localStorage.getItem('currentUser')!));
        this.currentUser = this.currentUserSubject.asObservable();
    }

    public get currentUserValue(): User {
        return this.currentUserSubject.value;
    }

    login(username: string, password: string) {
        return this.http.post<any>(`${environment.apiUrl}/authenticate`, { username, password })
            .pipe(map(user => {
                // store user details and jwt token in local storage to keep user logged in between page refreshes
                localStorage.setItem('currentUser', JSON.stringify(user));
                
                this.currentUserSubject.next(user);

                // Get user data and store in local storage
                this.getUserByToken().pipe(first()).subscribe(userData => {
                    this.userLocalData = userData;
                    console.log(JSON.stringify(userData));
                    this.currentUserId = this.userLocalData[0].id;
                    localStorage.setItem('currentUserId', JSON.stringify(this.currentUserId));
                })
                return user;
            }));
    }

    register(firstName: string, lastName: string, userName: string, password: string, securityLevel: number, 
        email: string, role: string) {
        return this.http.post<any>(`${environment.apiUrl}/register`, { firstName, lastName, userName, 
            password, securityLevel, email, role })
            .pipe(map(user => {
                // store user details and jwt token in local storage to keep user logged in between page refreshes
                localStorage.setItem('currentUser', JSON.stringify(user));
                this.currentUserSubject.next(user);
                return user;
            }));
    }

    logout() {
        // remove user from local storage to log user out
        localStorage.removeItem('currentUser');
        localStorage.removeItem('currentUserId');
        this.currentUserSubject.next(null!);
    }


    getUserByToken() {
        console.log(`In getUserByToken()`)
        return this.http.get<UserLocalStorage[]>(`${environment.apiUrl}/getUserByToken`);
    }

}
