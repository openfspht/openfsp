import { cn } from "@workspace/ui/lib/utils"

export function Logo({ className }: { className?: string }) {
    return (
        <svg
            viewBox="0 0 100 100"
            role="img"
            aria-label="OpenFSP"
            className={cn("size-7", className)}
        >
            <path
                className="fill-[#0B3B36] dark:fill-[#F5F2EA]"
                d="M72.18,82.079A39,39 0 0,1 14.752,66.692L28.63,62.973A25,25 0 0,0 62.021,71.92Z"
            />
            <path
                className="fill-[#0B3B36] dark:fill-[#F5F2EA]"
                d="M11.129,53.169A39,39 0 0,1 53.169,11.129L49.45,25.006A25,25 0 0,0 25.006,49.45Z"
            />
            <path
                fill="#E8A33D"
                d="M66.692,14.752A39,39 0 0,1 82.079,72.18L71.92,62.021A25,25 0 0,0 62.973,28.63Z"
            />
        </svg>
    )
}
