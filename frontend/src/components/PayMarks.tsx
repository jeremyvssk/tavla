// The cards and wallets we take, as small brand marks: Visa, Mastercard and Amex, then Apple Pay and Google Pay.
export function CardMarks() {
  return (
    <>
      <svg className="cmk" viewBox="0 0 46 30" aria-hidden="true">
        <rect width="46" height="30" rx="4" fill="#1A1F71" />
        <text x="23" y="19.5" textAnchor="middle" fontFamily="Arial, Helvetica, sans-serif" fontWeight="700" fontStyle="italic"
          fontSize="12.5" letterSpacing=".4" fill="#FFFFFF">VISA</text>
      </svg>
      <svg className="cmk ring" viewBox="0 0 46 30" aria-hidden="true">
        <rect width="46" height="30" rx="4" fill="#FFFFFF" />
        <circle cx="18.6" cy="15" r="8" fill="#EB001B" />
        <circle cx="27.4" cy="15" r="8" fill="#F79E1B" />
        <path d="M23 8.45a8 8 0 0 1 0 13.1 8 8 0 0 1 0-13.1z" fill="#FF5F00" />
      </svg>
      <svg className="cmk" viewBox="0 0 46 30" aria-hidden="true">
        <rect width="46" height="30" rx="4" fill="#2E77BC" />
        <text x="23" y="18.8" textAnchor="middle" fontFamily="Arial, Helvetica, sans-serif" fontWeight="700" fontSize="10"
          letterSpacing=".6" fill="#FFFFFF">AMEX</text>
      </svg>
    </>
  );
}

export default function PayMarks() {
  return (
    <div className="payicons" role="img" aria-label="We accept Visa, Mastercard, American Express, Apple Pay and Google Pay">
      <CardMarks />
      <span className="wmk">Apple Pay</span>
      <span className="wmk g">Google Pay</span>
    </div>
  );
}
