import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';
import { I18n } from '../i18n/i18n';
import { formatPrice } from './price';
import { ReservationForm } from './reservation-form';
import { ReservationInput, ReservationResult, StoreApi } from './store-api';

/** No-break spaces vary between browsers, and do not matter here. */
function plain(text: string): string {
  return text.replace(/\s/g, ' ');
}

describe('formatPrice', () => {
  it('shows euros the way each language writes them', () => {
    expect(plain(formatPrice(12345.5, 'lv'))).toBe('12 345,50 €');
    expect(plain(formatPrice(12345.5, 'ru'))).toBe('12 345,50 €');
    expect(plain(formatPrice(12345.5, 'en'))).toBe('€12,345.50');
  });
});

describe('ReservationForm', () => {
  let fixture: ComponentFixture<ReservationForm>;
  let root: HTMLElement;
  let reserve: (input: ReservationInput) => Observable<ReservationResult>;
  let sent: ReservationInput[];

  beforeEach(async () => {
    sent = [];
    reserve = () => of({ reference: 'XF-000123' });
    TestBed.configureTestingModule({
      imports: [ReservationForm],
      providers: [
        provideRouter([]),
        {
          provide: StoreApi,
          useValue: {
            reserve: (input: ReservationInput) => {
              sent.push(input);
              return reserve(input);
            },
          },
        },
      ],
    });
    await TestBed.inject(I18n).use('ru');
    fixture = TestBed.createComponent(ReservationForm);
    fixture.componentRef.setInput('slug', 'audi-a4');
    await fixture.whenStable();
    root = fixture.nativeElement;
  });

  function type(name: string, value: string): void {
    const input = root.querySelector<HTMLInputElement>(`[formControlName="${name}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function fillIn(): void {
    type('name', 'Anna Bērziņa');
    type('phone', '+371 20000000');
    type('email', 'anna@example.com');
    root.querySelector<HTMLInputElement>('input[type="checkbox"]')!.click();
  }

  async function submit(): Promise<void> {
    root.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('sends nothing until the form is right, and says what is wrong', async () => {
    await submit();

    expect(sent).toEqual([]);
    expect(root.textContent).toContain('Пожалуйста, заполните это поле.');
    expect(root.textContent).toContain('правильный номер телефона');
    expect(root.textContent).toContain('правильный адрес эл. почты');
    expect(root.textContent).toContain('отметьте это поле');

    fillIn();
    type('phone', 'zvani man');
    await submit();
    expect(sent).toEqual([]);
  });

  it('sends the reservation in the page language and shows its number', async () => {
    fillIn();

    await submit();

    expect(sent).toEqual([
      {
        slug: 'audi-a4',
        quantity: 1,
        name: 'Anna Bērziņa',
        phone: '+371 20000000',
        email: 'anna@example.com',
        comment: '',
        language: 'ru',
        privacyAccepted: true,
        website: '',
      },
    ]);
    expect(root.querySelector('form')).toBeNull();
    expect(root.querySelector('[role="status"]')?.textContent).toContain('XF-000123');
  });

  it('does not send twice while waiting for the answer', async () => {
    const answer = new Subject<ReservationResult>();
    reserve = () => answer;
    fillIn();

    root.querySelector('form')!.dispatchEvent(new Event('submit'));
    root.querySelector('form')!.dispatchEvent(new Event('submit'));
    answer.next({ reference: 'XF-1' });
    answer.complete();
    await fixture.whenStable();

    expect(sent.length).toBe(1);
  });

  it.each([
    [409, 'OUT_OF_STOCK', 'такого количества уже нет'],
    [404, 'PRODUCT_NOT_FOUND', 'больше недоступен'],
    [429, 'TOO_MANY_REQUESTS', 'Слишком много'],
    [500, undefined, 'Не удалось отправить'],
  ])('explains an answer of %s %s', async (status, code, text) => {
    reserve = () => throwError(() => new HttpErrorResponse({ status, error: { code } }));
    fillIn();

    await submit();

    expect(root.querySelector('[role="alert"]')?.textContent).toContain(text);
    expect(root.querySelector('form')).not.toBeNull();
  });

  it('tells the page when the product has run out', async () => {
    let told = 0;
    fixture.componentInstance.outOfStock.subscribe(() => told++);
    reserve = () => throwError(() => new HttpErrorResponse({ status: 409, error: { code: 'OUT_OF_STOCK' } }));
    fillIn();

    await submit();

    expect(told).toBe(1);
  });
});
