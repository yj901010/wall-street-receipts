export function formatMoney(value: number | null, currency: string | null) {
  if (value === null || currency === null || !/^[A-Z]{3}$/.test(currency)) {
    return "NA";
  }

  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(value);
}
