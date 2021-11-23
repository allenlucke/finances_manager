import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PeriodPostComponent } from './period-post.component';

describe('PeriodPostComponent', () => {
  let component: PeriodPostComponent;
  let fixture: ComponentFixture<PeriodPostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ PeriodPostComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(PeriodPostComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
