import { PreferenceCurrency } from '../api/trade.service';

export const USD_INR_RATE = 96;

function roundToTwo(value: number): number {
  return Math.round(value * 100) / 100;
}

export function convertBalanceForDisplay(
  amount: number,
  accountBaseCurrency: PreferenceCurrency,
  preferenceCurrency: PreferenceCurrency
): number {
  if (accountBaseCurrency === preferenceCurrency) {
    return amount;
  }

  if (accountBaseCurrency === 'USD' && preferenceCurrency === 'INR') {
    return roundToTwo(amount * USD_INR_RATE);
  }

  return roundToTwo(amount / USD_INR_RATE);
}
