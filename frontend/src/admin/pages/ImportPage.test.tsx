// The bulk upload page: a file the server can't read says why, and a check with errors can't be imported.
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import * as adminApi from '../../api/admin';
import { renderWithProviders } from '../../test/renderWithProviders';
import ImportPage from './ImportPage';

vi.mock('../../api/admin', async (actual) => ({
  ...(await actual<typeof import('../../api/admin')>()), startImport: vi.fn(), fetchImport: vi.fn(), confirmImport: vi.fn(),
}));

const job = (errors: number): adminApi.ImportJob => ({
  id: 'j1', fileName: 'products.csv', format: 'csv', sizeBytes: 2048, uploadedAt: '2026-10-12T10:00:00Z', rows: 3, status: 'CHECKED',
  onMatch: 'update', counts: { new: 2, update: 0, error: errors, photos: 0, done: 0 },
  errors: errors ? [{ line: 3, column: 'category', problem: 'No category has this slug', value: 'nope' }] : [], changes: [], failure: null,
});

describe('ImportPage', () => {
  afterEach(() => vi.clearAllMocks());

  it("shows the server's reason when the file can't be read at all", async () => {
    vi.mocked(adminApi.startImport).mockRejectedValue(new AxiosError('Bad Request', '400', undefined, undefined, {
      status: 400, statusText: 'Bad Request', headers: {}, config: { headers: new AxiosHeaders() },
      data: { error: 'invalid_import_file', fields: { file: 'Unknown column "colour".' } },
    }));
    renderWithProviders(<ImportPage />, { path: '/admin/import' });

    await userEvent.upload(screen.getByLabelText(/Drop a CSV or JSON file/), new File(['sku,colour\n'], 'p.csv', { type: 'text/csv' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Unknown column "colour".');
  });

  it('lists the rows with errors and keeps Import shut until the file is fixed', async () => {
    vi.mocked(adminApi.fetchImport).mockResolvedValue(job(1));
    renderWithProviders(<ImportPage />, { path: '/admin/import', url: '/admin/import?job=j1' });

    expect(await screen.findByText('No category has this slug')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Fix the errors to import' })).toBeDisabled();
    expect(adminApi.confirmImport).not.toHaveBeenCalled();
  });

  it('imports a clean file in one go', async () => {
    vi.mocked(adminApi.fetchImport).mockResolvedValue(job(0));
    vi.mocked(adminApi.confirmImport).mockResolvedValue({ ...job(0), status: 'DONE', counts: { ...job(0).counts, done: 2 } });
    renderWithProviders(<ImportPage />, { path: '/admin/import', url: '/admin/import?job=j1' });

    await userEvent.click(await screen.findByRole('button', { name: 'Import 2 rows' }));

    expect(adminApi.confirmImport).toHaveBeenCalledWith('j1', 'update');
    expect(await screen.findByText('2 products added or updated.')).toBeInTheDocument();
  });
});
