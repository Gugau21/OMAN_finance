import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import App from './App.jsx';

describe('App', () => {
  it('increments the counter when the button is clicked', () => {
    render(<App />);

    const counter = screen.getByRole('button', { name: 'Count is 0' });
    expect(counter).toBeInTheDocument();

    fireEvent.click(counter);

    expect(screen.getByRole('button', { name: 'Count is 1' })).toBeInTheDocument();
  });
});
