import { Component, inject, input, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom, Observable } from 'rxjs';
import { errorMessage } from '../admin-errors';
import { Product, ProductApi, ProductImage, ProductInput, ProductLanguage, ProductStatus, STATUS_NAMES } from './product-api';

const LANGUAGES: { code: ProductLanguage; name: string }[] = [
  { code: 'lv', name: 'Latviski' },
  { code: 'ru', name: 'Krieviski' },
  { code: 'en', name: 'Angliski' },
];

/** The same limit as the backend, so a too large photo is refused before a long upload. */
const MAX_FILE_BYTES = 20 * 1024 * 1024;

@Component({
  selector: 'app-product-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './product-form.html',
  styleUrls: ['../admin.css', './product-form.css'],
})
export class ProductForm implements OnInit {
  private readonly api = inject(ProductApi);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  /** From the address (see withComponentInputBinding); missing for a new product. */
  readonly id = input<string>();

  protected readonly languages = LANGUAGES;
  protected readonly statusNames = STATUS_NAMES;
  protected readonly product = signal<Product | null>(null);
  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  protected readonly saved = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly uploading = signal(false);
  protected readonly imageErrors = signal<string[]>([]);

  protected readonly form = this.formBuilder.group({
    slug: ['', [Validators.maxLength(200), Validators.pattern(/^[a-z0-9]+(-[a-z0-9]+)*$/)]],
    partNumber: ['', Validators.maxLength(200)],
    price: this.formBuilder.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    salePrice: this.formBuilder.control<number | null>(null, Validators.min(0.01)),
    stockQuantity: this.formBuilder.control<number | null>(0, [Validators.required, Validators.min(0)]),
    translations: this.formBuilder.group({
      lv: this.textsGroup(true),
      ru: this.textsGroup(false),
      en: this.textsGroup(false),
    }),
  });

  private textsGroup(required: boolean) {
    return this.formBuilder.group({
      name: ['', required ? [Validators.required, Validators.maxLength(200)] : Validators.maxLength(200)],
      description: ['', Validators.maxLength(5000)],
    });
  }

  async ngOnInit(): Promise<void> {
    const id = this.id();
    if (!id) {
      return;
    }
    this.loading.set(true);
    try {
      this.show(await firstValueFrom(this.api.get(Number(id))));
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }

  protected async save(): Promise<void> {
    this.saved.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Pārbaudiet aizpildītos laukus. Latviskais nosaukums, cena un atlikums ir obligāti.');
      return;
    }
    const value = this.form.getRawValue();
    const input: ProductInput = {
      ...value,
      price: value.price!,
      stockQuantity: value.stockQuantity!,
      version: this.product()?.version,
    };
    const current = this.product();
    this.busy.set(true);
    this.error.set(null);
    try {
      if (current) {
        this.show(await firstValueFrom(this.api.update(current.id, input)));
        this.saved.set('Saglabāts.');
      } else {
        const created = await firstValueFrom(this.api.create(input));
        await this.router.navigate(['/admin/products', created.id]);
      }
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }

  protected changeStatus(status: ProductStatus): Promise<void> {
    return this.apply((id) => this.api.changeStatus(id, status));
  }

  protected removeImage(image: ProductImage): Promise<void> {
    return this.apply((id) => this.api.removeImage(id, image.id));
  }

  /** Moves a photo earlier or later. The first photo is the product's main one. */
  protected moveImage(index: number, by: number): Promise<void> {
    const ids = this.product()!.images.map((image) => image.id);
    [ids[index], ids[index + by]] = [ids[index + by], ids[index]];
    return this.apply((id) => this.api.orderImages(id, ids));
  }

  /** Uploads one by one, so one bad file does not stop the others. */
  protected async upload(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = [...(input.files ?? [])];
    input.value = '';
    const id = this.product()?.id;
    if (id === undefined) {
      return;
    }
    this.uploading.set(true);
    this.imageErrors.set([]);
    const errors: string[] = [];
    for (const file of files) {
      try {
        if (file.size > MAX_FILE_BYTES) {
          errors.push(`${file.name}: fails ir lielāks par 20 MB.`);
          continue;
        }
        this.showImages(await firstValueFrom(this.api.addImage(id, file)));
      } catch (error) {
        errors.push(`${file.name}: ${errorMessage(error)}`);
      }
    }
    this.imageErrors.set(errors);
    this.uploading.set(false);
  }

  private async apply(action: (id: number) => Observable<Product>): Promise<void> {
    this.error.set(null);
    this.saved.set(null);
    try {
      this.showImages(await firstValueFrom(action(this.product()!.id)));
    } catch (error) {
      this.error.set(errorMessage(error));
    }
  }

  /** Keeps what the admin has typed but not saved; only what these actions change is taken over. */
  private showImages(product: Product): void {
    this.product.set(product);
  }

  private show(product: Product): void {
    this.product.set(product);
    const texts = (language: ProductLanguage) => ({
      name: product.translations[language]?.name ?? '',
      description: product.translations[language]?.description ?? '',
    });
    this.form.reset({
      slug: product.slug,
      partNumber: product.partNumber ?? '',
      price: product.price,
      salePrice: product.salePrice,
      stockQuantity: product.stockQuantity,
      translations: { lv: texts('lv'), ru: texts('ru'), en: texts('en') },
    });
  }
}
