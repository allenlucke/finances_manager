import { Component } from '@angular/core';
import { Router } from '@angular/router';

import * as $ from 'jquery';

import { AuthenticationService } from 'src/app/service/authentication.service';
import { User } from 'src/app/_models/user';

@Component({ selector: 'app', templateUrl: 'app.component.html',   styleUrls: ['./app.component.css'] })
export class AppComponent {
    currentUser!: User;
    title = 'financesAngular';

    constructor(
        private router: Router,
        private authenticationService: AuthenticationService
    ) {
        this.authenticationService.currentUser.subscribe(x => this.currentUser = x);
    }

    ngOnInit() {
        //Toggle Click Function
        $("#menu-toggle").click(function(e) {
        e.preventDefault();
        $("#wrapper").toggleClass("toggled");
        });

        // $('.dropdown').click(function(){
        //     $('.dropdown-menu').toggleClass('show');
        //   });
    }

    logout() {
        this.authenticationService.logout();
        this.router.navigate(['/login']);
    }
    
}
