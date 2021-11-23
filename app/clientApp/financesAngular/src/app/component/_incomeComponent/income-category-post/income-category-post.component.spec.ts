import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IncomeCategoryPostComponent } from './income-category-post.component';

describe('IncomeCategoryPostComponent', () => {
  let component: IncomeCategoryPostComponent;
  let fixture: ComponentFixture<IncomeCategoryPostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ IncomeCategoryPostComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(IncomeCategoryPostComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
