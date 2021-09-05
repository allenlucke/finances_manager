import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TestBalSheetComponent } from './test-bal-sheet.component';

describe('TestBalSheetComponent', () => {
  let component: TestBalSheetComponent;
  let fixture: ComponentFixture<TestBalSheetComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ TestBalSheetComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(TestBalSheetComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
