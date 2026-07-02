import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Ajouter } from './ajouter';

describe('Ajouter', () => {
  let component: Ajouter;
  let fixture: ComponentFixture<Ajouter>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Ajouter],
    }).compileComponents();

    fixture = TestBed.createComponent(Ajouter);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
