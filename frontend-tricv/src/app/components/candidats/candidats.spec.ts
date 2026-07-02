import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Candidats } from './candidats';

describe('Candidats', () => {
  let component: Candidats;
  let fixture: ComponentFixture<Candidats>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Candidats],
    }).compileComponents();

    fixture = TestBed.createComponent(Candidats);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
