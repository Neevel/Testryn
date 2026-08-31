import { useEffect, useState } from "react";
type Theme = "system" | "light" | "dark";
type Design =
  | "guild" | "arcane" | "dragonforge" | "focus" | "slate"
  | "nebula" | "lunar" | "solar"
  | "celquest" | "neonshonen" | "cozystudio"
  | "synthwave" | "holoterminal" | "mechacore"
  | "emerald" | "ocean" | "desert";
const THEME_KEY = "testryn-theme";
const DESIGN_KEY = "testryn-design";
export function ThemeSelector() {
  const [theme, setTheme] = useState<Theme>(() => (localStorage.getItem(THEME_KEY) as Theme) || "system");
  const [design, setDesign] = useState<Design>(() => (localStorage.getItem(DESIGN_KEY) as Design) || "guild");
  useEffect(() => {
    if (theme === "system") document.documentElement.removeAttribute("data-theme");
    else document.documentElement.dataset.theme = theme;
    localStorage.setItem(THEME_KEY, theme);
  }, [theme]);
  useEffect(() => {
    document.documentElement.dataset.design = design;
    localStorage.setItem(DESIGN_KEY, design);
  }, [design]);
  return <div className="theme-selector">
    <label><span>Design</span><select aria-label="Interface design" value={design} onChange={(e) => setDesign(e.target.value as Design)}>
      <optgroup label="Fantasy & RPG">
        <option value="guild">Guild Chronicle</option>
        <option value="arcane">Arcane Observatory</option>
        <option value="dragonforge">Dragonforge</option>
      </optgroup>
      <optgroup label="Classic">
        <option value="focus">Focus</option>
        <option value="slate">Slate</option>
      </optgroup>
      <optgroup label="Space & Cosmos">
        <option value="nebula">Nebula Command</option>
        <option value="lunar">Lunar Colony</option>
        <option value="solar">Solar Vanguard</option>
      </optgroup>
      <optgroup label="Animated Worlds">
        <option value="celquest">Cel Quest</option>
        <option value="neonshonen">Mythic Overdrive · Animated D&amp;D</option>
        <option value="cozystudio">Cozy Studio</option>
      </optgroup>
      <optgroup label="Cyber Realms">
        <option value="synthwave">Synthwave Grid</option>
        <option value="holoterminal">Holo Terminal</option>
        <option value="mechacore">Mecha Core</option>
      </optgroup>
      <optgroup label="Natural Worlds">
        <option value="emerald">Emerald Grove</option>
        <option value="ocean">Ocean Depths</option>
        <option value="desert">Desert Dawn</option>
      </optgroup>
    </select></label>
    <label><span>Mode</span><select aria-label="Color mode" value={theme} onChange={(e) => setTheme(e.target.value as Theme)}>
      <option value="system">System</option><option value="light">Light</option><option value="dark">Dark</option>
    </select></label>
  </div>;
}
