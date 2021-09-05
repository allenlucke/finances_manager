import { Component, OnInit } from '@angular/core';
import { first } from 'rxjs/operators';
import { RestApiService } from "../../service/rest-api.service";
import { AuthenticationService } from '../../service/authentication.service';

@Component({
  selector: 'app-hello',
  templateUrl: './hello.component.html',
  styleUrls: ['./hello.component.css']
})
export class HelloComponent implements OnInit {
  loading = false;
  Hello: any = [];

  constructor(
    public restApi: RestApiService
  ) { }

  ngOnInit() {
    this.loading = true;
    console.log("In hello component ng on init")
    this.loadHello();
  }

  // Get hello
  loadHello() {
    return this.restApi.getHello().subscribe((data: {}) => {
      console.log("Hello response: " + JSON.stringify(data))
      this.loading = false;
      this.Hello = [data];
    })
  }
}
