import { Component } from '@angular/core';
import { first } from 'rxjs/operators';

import { User } from '../../_models/user';
import { UserService } from '../../service/user.service';
import { AuthenticationService } from '../../service/authentication.service';


@Component({ templateUrl: 'home.component.html' })
export class HomeComponent {
    loading = false;
    users!: User[];

    constructor(private userService: UserService,
                private authService: AuthenticationService
        ) { }

    ngOnInit() {
        this.loading = true;
        this.userService.getAll().pipe(first()).subscribe(users => {
            this.loading = false;
            this.users = users;
        });

        console.log(localStorage.getItem('currentUserId'));

    }
}
