import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { adminRoutes } from '../admin.routes';
import { Product, ProductSummary } from './product-api';
import { ProductForm } from './product-form';
import { ProductList } from './product-list';

const SUMMARY: ProductSummary = {
  id: 7,
  name: 'Audi A5 lukturis',
  slug: 'audi-a5-lukturis',
  partNumber: '8T0.941.032C',
  price: 450,
  salePrice: 420,
  stockQuantity: 0,
  status: 'PUBLISHED',
  thumbnailUrl: '/uploads/products/7/a-thumb.jpg',
};

const PRODUCT: Product = {
  id: 7,
  slug: 'audi-a5-lukturis',
  partNumber: '8T0.941.032C',
  price: 450,
  salePrice: null,
  stockQuantity: 2,
  status: 'DRAFT',
  translations: { lv: { name: 'Audi A5 lukturis', description: 'Apraksts' }, ru: { name: 'Фара Audi A5', description: null } },
  images: [
    { id: 1, url: '/uploads/a.jpg', thumbnailUrl: '/uploads/a-thumb.jpg', width: 800, height: 600 },
    { id: 2, url: '/uploads/b.jpg', thumbnailUrl: '/uploads/b-thumb.jpg', width: 800, height: 600 },
  ],
  version: 3,
};

describe('Admin products', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'admin', children: adminRoutes }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  async function request(url: string): Promise<TestRequest> {
    for (let turn = 0; turn < 100; turn++) {
      const [found] = http.match((pending) => pending.url === url);
      if (found) {
        return found;
      }
      await new Promise((resolve) => setTimeout(resolve));
    }
    throw new Error(`No request to ${url}`);
  }

  async function settled(fixture: ComponentFixture<unknown>): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 10));
    await fixture.whenStable();
  }

  async function openForm(): Promise<ComponentFixture<ProductForm>> {
    const fixture = TestBed.createComponent(ProductForm);
    fixture.componentRef.setInput('id', '7');
    fixture.detectChanges();
    (await request('/api/admin/products/7')).flush(PRODUCT);
    await settled(fixture);
    return fixture;
  }

  function field(root: HTMLElement, name: string): HTMLInputElement {
    return root.querySelector<HTMLInputElement>(`[formControlName="${name}"]`)!;
  }

  function button(root: HTMLElement, text: string): HTMLButtonElement {
    return [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === text)!;
  }

  it('lists products with their sale price, stock and status', async () => {
    const fixture = TestBed.createComponent(ProductList);
    fixture.detectChanges();
    (await request('/api/admin/products')).flush({ items: [SUMMARY], page: 0, totalPages: 1 });
    await settled(fixture);
    const row = (fixture.nativeElement as HTMLElement).querySelector('tbody tr')!;

    expect(row.querySelector('a')?.getAttribute('href')).toBe('/admin/products/7');
    expect(row.querySelector('s')?.textContent).toBe('450.00 €');
    expect(row.textContent).toContain('420.00 €');
    expect(row.querySelector('.out-of-stock')?.textContent).toBe('0');
    expect(row.textContent).toContain('Publicēts');
  });

  it('searches and filters by status', async () => {
    const fixture = TestBed.createComponent(ProductList);
    fixture.detectChanges();
    (await request('/api/admin/products')).flush({ items: [], page: 0, totalPages: 0 });
    await settled(fixture);
    const root = fixture.nativeElement as HTMLElement;
    const search = root.querySelector<HTMLInputElement>('input[type="search"]')!;
    search.value = ' audi ';
    search.dispatchEvent(new Event('input'));

    root.querySelector('form')?.dispatchEvent(new Event('submit'));
    const pending = await request('/api/admin/products');

    expect(pending.request.params.get('search')).toBe('audi');
    expect(root.textContent).toContain('Nav atrasts neviens produkts');
  });

  it('does not send a new product without the required fields', async () => {
    const fixture = TestBed.createComponent(ProductForm);
    fixture.detectChanges();
    await settled(fixture);
    const root = fixture.nativeElement as HTMLElement;

    root.querySelector('form')?.dispatchEvent(new Event('submit'));
    await settled(fixture);

    http.expectNone('/api/admin/products');
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('obligāti');
  });

  it('creates a product and opens it, so photos can be added', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ProductForm);
    fixture.detectChanges();
    await settled(fixture);
    const root = fixture.nativeElement as HTMLElement;
    const type = (element: HTMLInputElement, value: string) => {
      element.value = value;
      element.dispatchEvent(new Event('input'));
    };
    type(field(root, 'price'), '450');
    type(root.querySelector<HTMLInputElement>('fieldset input')!, 'Audi A5 lukturis');

    root.querySelector('form')?.dispatchEvent(new Event('submit'));
    const pending = await request('/api/admin/products');

    expect(pending.request.body.price).toBe(450);
    expect(pending.request.body.translations.lv.name).toBe('Audi A5 lukturis');
    pending.flush(PRODUCT);
    await settled(fixture);
    expect(navigate).toHaveBeenCalledWith(['/admin/products', 7]);
  });

  it('saves with the version it was shown, so it cannot overwrite unseen changes', async () => {
    const fixture = await openForm();
    const root = fixture.nativeElement as HTMLElement;
    expect(field(root, 'stockQuantity').value).toBe('2');
    expect(root.querySelectorAll('fieldset input')[1]).toHaveProperty('value', 'Фара Audi A5');

    root.querySelector('form')?.dispatchEvent(new Event('submit'));
    const pending = await request('/api/admin/products/7');

    expect(pending.request.method).toBe('PUT');
    expect(pending.request.body.version).toBe(3);
    pending.flush({ ...PRODUCT, version: 4 });
    await settled(fixture);
    expect(root.querySelector('[role="status"]')?.textContent).toBe('Saglabāts.');
  });

  it('explains when another admin changed the product first', async () => {
    const fixture = await openForm();
    const root = fixture.nativeElement as HTMLElement;

    root.querySelector('form')?.dispatchEvent(new Event('submit'));
    (await request('/api/admin/products/7')).flush({ code: 'CHANGED_BY_ANOTHER' }, { status: 409, statusText: 'Conflict' });
    await settled(fixture);

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Kāds cits');
  });

  it('keeps unsaved typing when the photos or status change', async () => {
    const fixture = await openForm();
    const root = fixture.nativeElement as HTMLElement;
    const stock = field(root, 'stockQuantity');
    stock.value = '9';
    stock.dispatchEvent(new Event('input'));

    button(root, 'Publicēt').click();
    const pending = await request('/api/admin/products/7/status');

    expect(pending.request.body).toEqual({ status: 'PUBLISHED' });
    pending.flush({ ...PRODUCT, status: 'PUBLISHED', version: 4 });
    await settled(fixture);
    expect(field(root, 'stockQuantity').value).toBe('9');
    expect(root.querySelector('.status')?.textContent).toBe('Publicēts');
  });

  it('moves a photo earlier and cannot move the first one further', async () => {
    const fixture = await openForm();
    const root = fixture.nativeElement as HTMLElement;
    const items = root.querySelectorAll('.images li');
    expect(items[0].querySelector<HTMLButtonElement>('button')?.disabled).toBe(true);

    items[1].querySelector<HTMLButtonElement>('button')?.click();
    const pending = await request('/api/admin/products/7/images/order');

    expect(pending.request.body).toEqual({ imageIds: [2, 1] });
  });

  it('uploads each photo separately and reports the ones that fail', async () => {
    const fixture = await openForm();
    const root = fixture.nativeElement as HTMLElement;
    const input = root.querySelector<HTMLInputElement>('input[type="file"]')!;
    const files = [new File(['a'], 'good.jpg'), new File(['b'], 'bad.jpg')];
    Object.defineProperty(input, 'files', { value: files });

    input.dispatchEvent(new Event('change'));
    const first = await request('/api/admin/products/7/images');
    expect((first.request.body as FormData).get('file')).toBe(files[0]);
    first.flush(PRODUCT);
    (await request('/api/admin/products/7/images')).flush({ code: 'INVALID_IMAGE' }, { status: 400, statusText: 'Bad Request' });
    await settled(fixture);

    expect(root.querySelector('.panel [role="alert"]')?.textContent).toContain('bad.jpg');
    expect(root.querySelector('.panel [role="alert"]')?.textContent).not.toContain('good.jpg');
  });
});
