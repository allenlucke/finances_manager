import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IncomeItemPostComponent } from './income-item-post.component';

describe('IncomeItemPostComponent', () => {
  let component: IncomeItemPostComponent;
  let fixture: ComponentFixture<IncomeItemPostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ IncomeItemPostComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(IncomeItemPostComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
