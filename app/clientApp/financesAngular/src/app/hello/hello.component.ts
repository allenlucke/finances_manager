import { Component, OnInit } from '@angular/core';
import { RestApiService } from "../service/rest-api.service";

@Component({
  selector: 'app-hello',
  templateUrl: './hello.component.html',
  styleUrls: ['./hello.component.css']
})
export class HelloComponent implements OnInit {

  Hello: any = [];

  constructor(
    public restApi: RestApiService
  ) { }

  ngOnInit() {
    console.log("In hello component ng on init")
    this.loadHello();
  }

  // Get hello
  loadHello() {
    return this.restApi.getHello().subscribe((data: {}) => {
      console.log("Hello response: " + JSON.stringify(data))
    })
  }
}
