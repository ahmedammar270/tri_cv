import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Recherche } from './recherche';

describe('Recherche', () => {
  let component: Recherche;
  let fixture: ComponentFixture<Recherche>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Recherche],
    }).compileComponents();

    fixture = TestBed.createComponent(Recherche);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
