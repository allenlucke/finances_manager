import { Component, OnInit } from '@angular/core';
import { RestApiService } from "../shared/rest-api.service";

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
    console.log("In ng on inti")
    this.restApi.getHello().subscribe((data: {}) => {
      console.log(JSON.stringify(data))
      this.Hello = JSON.stringify(data);
      console.log("data stuff " +JSON.stringify(data))
    })
    this.loadHello();
    // console.log(this.loadHello)
  }

  // Get hello
  loadHello() {
    console.log("in load hello")
    return this.restApi.getHello().subscribe((data: {}) => {
      this.Hello = JSON.stringify(data);
      console.log("data stuff " +JSON.stringify(data))
    })
  }
}
