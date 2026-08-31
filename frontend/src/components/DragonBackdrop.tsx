export function DragonBackdrop() {
  return (
    <div className="dragon-backdrop" aria-hidden="true">
      <svg viewBox="0 0 900 600" role="presentation">
        <defs>
          <radialGradient id="dragonAura" cx="55%" cy="48%" r="50%">
            <stop offset="0" stopColor="currentColor" stopOpacity=".28" />
            <stop offset="1" stopColor="currentColor" stopOpacity="0" />
          </radialGradient>
          <linearGradient id="dragonScale" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="currentColor" stopOpacity=".8" />
            <stop offset="1" stopColor="currentColor" stopOpacity=".28" />
          </linearGradient>
        </defs>
        <ellipse className="dragon-backdrop__aura" cx="510" cy="330" rx="330" ry="205" fill="url(#dragonAura)" />
        <g className="dragon-backdrop__dragon">
          <g className="dragon-backdrop__wing dragon-backdrop__wing-back">
            <path d="M476 325C421 210 309 100 130 94c89 54 145 116 169 190-49-40-103-59-165-61 71 44 128 92 168 151 53-6 112-20 174-49Z" />
            <path className="dragon-backdrop__wing-bone" d="M466 321C388 237 285 164 130 94M388 278c-70-21-151-38-254-55M350 309c-18-51-34-97-51-125" />
          </g>
          <g className="dragon-backdrop__wing dragon-backdrop__wing-front">
            <path d="M500 340c12-116 87-239 222-303-54 72-77 142-67 211 38-48 83-82 137-103-50 66-85 134-104 202-59 14-122 12-188-7Z" />
            <path className="dragon-backdrop__wing-bone" d="M507 333C559 229 626 126 722 37M590 283c58-54 124-101 202-138M626 287c6-55 17-102 29-139" />
          </g>
          <path className="dragon-backdrop__tail" d="M466 366C347 410 271 471 171 475c-72 3-108-34-84-76 17-30 65-36 91-7" />
          <path className="dragon-backdrop__body" d="M428 362c65 52 159 55 217-5 36-38 44-90 73-118" />
          <path className="dragon-backdrop__neck" d="M603 351c43-32 48-87 87-119" />
          <path className="dragon-backdrop__head" d="M674 238c29-31 68-45 112-35l-22 18 47 16-44 17 18 24-55-7-29 25-25-31Z" fill="url(#dragonScale)" />
          <path className="dragon-backdrop__horn" d="m704 217-4-46 25 36m23-4 20-40 2 48" />
          <path className="dragon-backdrop__leg" d="M555 380c-4 47 18 73 63 77m-10-9 17 17m-17-17 25 3M479 382c-30 31-29 65 4 91m-5-11-5 24m5-24 16 19" />
          <circle className="dragon-backdrop__eye" cx="761" cy="225" r="5" />
          <path className="dragon-backdrop__scales" d="m425 352 21-13 19 17 21-14 20 18 22-13 19 16" />
        </g>
      </svg>
    </div>
  );
}
