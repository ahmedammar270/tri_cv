import { TestBed } from '@angular/core/testing';

import { Candidat } from './candidat';

describe('Candidat', () => {
  let service: Candidat;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Candidat);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
