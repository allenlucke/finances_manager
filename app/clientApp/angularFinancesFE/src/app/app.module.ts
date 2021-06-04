import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
// HttpClient module for RESTful API
import { HttpClientModule } from '@angular/common/http';
// Forms module
import { FormsModule } from '@angular/forms'; 
// import { MatDatepickerModule } from '@angular/material/datepicker';

// import { MatDialogModule } from '@angular/material/dialog';
// import { MatTableModule } from '@angular/material/table';
// import { MatFormFieldModule } from '@angular/material/form-field';
// import { MatInputModule } from '@angular/material/input';
// import { MatButtonModule } from '@angular/material/button';
// Datatables
import {DataTablesModule} from 'angular-datatables';
// Routing module for router service
import { AppRoutingModule } from './app-routing.module';
// Components
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
    AppRoutingModule,
    DataTablesModule,
    // MatDatepickerModule, 
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
