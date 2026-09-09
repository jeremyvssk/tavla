// Landing page — hero section only, no navigation yet.
export default function HomePage() {
  return (
    <main className="hero">
      <div className="hero__inner">
        <p className="hero__eyebrow">Japan, delivered</p>
        <h1 className="hero__title">
          Everyday things,
          <br />
          made properly.
        </h1>
        <p className="hero__lede">
          Ceramics, stationery, kitchenware and tea — sourced from small Japanese makers
          and shipped worldwide.
        </p>
        <a className="hero__cta" href="/products">
          Browse the catalog
        </a>
      </div>
    </main>
  );
}
