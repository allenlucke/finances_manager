import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BudgetCloneComponent } from './budget-clone.component';

describe('BudgetCloneComponent', () => {
  let component: BudgetCloneComponent;
  let fixture: ComponentFixture<BudgetCloneComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ BudgetCloneComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(BudgetCloneComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
