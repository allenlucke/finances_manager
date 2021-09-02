
import { Component } from '@angular/core';
import { Router } from '@angular/router';

import { AuthenticationService } from 'src/app/service/authentication.service';
import { User } from 'src/app/_models/user';

@Component({ selector: 'app', templateUrl: 'app.component.html' })
export class AppComponent {
    currentUser!: User;
    title = 'financesAngular';

    constructor(
        private router: Router,
        private authenticationService: AuthenticationService
    ) {
        this.authenticationService.currentUser.subscribe(x => this.currentUser = x);
    }

    logout() {
        this.authenticationService.logout();
        this.router.navigate(['/login']);
    }
    
}
