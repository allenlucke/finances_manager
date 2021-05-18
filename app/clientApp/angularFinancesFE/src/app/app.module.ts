import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
// HttpClient module for RESTful API
import { HttpClientModule } from '@angular/common/http';
// Forms module
import { FormsModule } from '@angular/forms'; 
// Routing module for router service
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { ExpensesComponent } from './expenses/expenses.component';
import { HelloComponent } from './hello/hello.component';

@NgModule({
  declarations: [
    AppComponent,
    ExpensesComponent,
    HelloComponent
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    FormsModule,
    AppRoutingModule
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }