import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@workspace/ui/components/select"

const ALL_OPTION = "__all__"

interface BaseProps<T extends string> {
    options: readonly T[]
    labelOf?: (value: T) => string
    label?: string
    className?: string
}

interface SingleProps<T extends string> extends BaseProps<T> {
    allLabel?: undefined
    value: T
    onChange: (value: T) => void
}

interface WithAllProps<T extends string> extends BaseProps<T> {
    allLabel: string
    value: T | ""
    onChange: (value: T | "") => void
}

type OptionSelectProps<T extends string> = SingleProps<T> | WithAllProps<T>

export function OptionSelect<T extends string>(props: OptionSelectProps<T>) {
    const { options, labelOf = (option: T) => option, label, className = "w-56" } = props
    const allLabel = props.allLabel
    const items: readonly string[] = allLabel === undefined ? options : [ALL_OPTION, ...options]
    const value = props.value === "" ? ALL_OPTION : props.value
    const text = (option: string) =>
        option === ALL_OPTION && allLabel !== undefined ? allLabel : labelOf(option as T)
    const change = (next: string) => {
        if (props.allLabel === undefined) {
            props.onChange(next as T)
        } else {
            props.onChange(next === ALL_OPTION ? "" : (next as T))
        }
    }
    return (
        <Select value={value} onValueChange={(next) => next && change(next)}>
            <SelectTrigger className={className} aria-label={label}>
                <SelectValue>{(selected: string) => text(selected)}</SelectValue>
            </SelectTrigger>
            <SelectContent>
                {items.map((option) => (
                    <SelectItem key={option} value={option}>
                        {text(option)}
                    </SelectItem>
                ))}
            </SelectContent>
        </Select>
    )
}
