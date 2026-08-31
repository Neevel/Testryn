import { Icon } from "./Icon";

export type EntityKind = "project" | "testCase" | "testPlan" | "execution";

const iconNames = {
  project: "projects",
  testCase: "testCases",
  testPlan: "testPlans",
  execution: "play",
} as const;

export function EntityIcon({ kind }: { kind: EntityKind }) {
  const cssKind = kind.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`);
  return <span className={`entity-icon entity-icon-${cssKind}`} aria-hidden="true"><Icon name={iconNames[kind]} /></span>;
}
